package chat.dim.sechat.account;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import chat.dim.sechat.R;

public class AccountFragment extends Fragment {

    private AccountViewModel mViewModel;

    private ImageView avatarView;
    private TextView nameView;
    private TextView descView;

    private ImageButton detailButton;

    private Button createButton;
    private Button termsButton;
    private Button aboutButton;

    public static AccountFragment newInstance() {
        return new AccountFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_fragment, container, false);

        avatarView = view.findViewById(R.id.avatarView);
        nameView = view.findViewById(R.id.nameView);
        descView = view.findViewById(R.id.descView);

        detailButton = view.findViewById(R.id.detailBtn);

        createButton = view.findViewById(R.id.createBtn);
        termsButton = view.findViewById(R.id.termBtn);
        aboutButton = view.findViewById(R.id.aboutBtn);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(AccountViewModel.class);
        // TODO: Use the ViewModel

        nameView.setText(mViewModel.getAccountTitle());
        descView.setText(mViewModel.getAccountDesc());
    }

}
